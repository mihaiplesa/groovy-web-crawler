@Grapes([
// Hazelcast dependency, distributed cache (map file persistence to be used for stateful crawler)
@Grab(group = 'com.hazelcast', module = 'hazelcast', version = '2.3.1'),
// Neko HTML parser, used for link extraction
@Grab(group = 'net.sourceforge.nekohtml', module = 'nekohtml', version = '1.9.16')
])

package ro.mihaiplesa.crawler

// initialize link map, key is URL, value is true if visited, false otherwise
Map<String, Boolean> linkMap = com.hazelcast.core.Hazelcast.getMap("links");
linkMap.put("http://mihaiplesa.ro", false)

// initialize Neko HTML parser and disable namespace processing
def parser = new org.cyberneko.html.parsers.SAXParser()
parser.setFeature('http://xml.org/sax/features/namespaces', false)

// crawl while we still have unvisited URLs
while ((e = linkMap.find {it.getValue() == false}) != null) {
    try {
        // get URL address from map value
        def url = e.getKey()

        // crawl URL and set visited to true
        print "Crawling ${url}..."
        e.setValue(true)
        crawlToDisk(url)

        // extract links from current URL
        def host = (url =~ /(http:\/\/[^\/]+)\/?.*/)[0][1]
        def base = url[0..url.lastIndexOf('/')]
        def page = new XmlParser(parser).parse(url)
        def links = page.depthFirst().A.grep { it.@href }.'@href'

        //  fix and put all new links to map, visited set to false
        links.each {
            def newURL = processURL(host, base, it)
            if (linkMap.containsKey(newURL) == false) {
                linkMap.put(newURL, false)
            }
        }

        println "found ${links.size()} URLs."
    }
    catch (Exception) { }
}

// fixes relative links and discards unwanted protocols
def processURL(host, base, url) {
    if (url.startsWith('http://') || url.startsWith('https://'))
        return url                                                  // URL is absolute, return it
    else if (url.startsWith('/'))
        return host + url                                           // URL is relative to host, prepend host
    else if (url.startsWith('mailto:') || url.startsWith('ftp://'))
        return null                                                 // discard unwanted protocols
    else
        return base + url                                           // URL is relative, prepend base
}

// crawls to disk website given by address
def crawlToDisk(websiteAddress) {
    def fullAddress = websiteAddress

    // add '/' to URL which lacks it
    if (websiteAddress.count('/') < 3) {
        fullAddress += '//'
    }

    // add 'index.html' to URL which points to a directory
    if (websiteAddress.lastIndexOf('.') < fullAddress.length() - 4) {
        fullAddress += "/index.html"
    }

    // fix URL - replace multiple '/'
    fullAddress.replace("///", "/")

    def url = new URL(fullAddress)

    // extract relative dir path from URL, ignore params and query part
    def dir = url.getHost() + url.getPath()
    dir = dir.substring(0, dir.lastIndexOf('/'))

    //  extract file name from URL
    def file = url.getPath().substring(url.getPath().lastIndexOf('/') + 1, url.getPath().length())

    // create local dir structure for crawled website
    new File(dir).mkdirs()

    // crawl website to file on disk
    def out = new BufferedOutputStream(new FileOutputStream(dir + '/' + file))
    out << new URL(websiteAddress).openStream()
    out.close()
}