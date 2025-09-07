import groovy.xml.XmlSlurper
import groovy.xml.MarkupBuilder
import groovy.io.FileType

// ---------------- Konfiguration ----------------
def baseDir = new File("C:\\Users\\breme\\Downloads\\example-poms") // Pfad zu den POM-Verzeichnissen
def groupFilter = "com.mycompany"          // Filter für groupId

// ---------------- POM-Dateien einlesen ----------------
def poms = []
baseDir.eachFileRecurse(FileType.FILES) { file ->
    if (file.name == "pom.xml") {
        poms << file
    }
}

println "Gefundene POM-Dateien: ${poms.size()}"

// ---------------- POM parsen ----------------
def parsePom(File pomFile) {
    def xml = new XmlSlurper().parse(pomFile)

    def project = [
            groupId     : xml.groupId.text() ?: xml.parent.groupId.text(),
            artifactId  : xml.artifactId.text(),
            version     : xml.version.text() ?: xml.parent.version.text(),
            parent      : null,
            dependencies: [],
            imports     : []
    ]

    if (xml.parent) {
        project.parent = [
                groupId   : xml.parent.groupId.text(),
                artifactId: xml.parent.artifactId.text(),
                version   : xml.parent.version.text()
        ]
    }

    xml.dependencies.dependency.each { dep ->
        project.dependencies << [
                groupId    : dep.groupId.text(),
                artifactId : dep.artifactId.text(),
                version    : dep.version.text(),
                scope      : dep.scope.text()
        ]
    }

    xml.dependencyManagement.dependencies.dependency.findAll { dep ->
        dep.scope.text() == "import"
    }.each { dep ->
        project.imports << [
                groupId    : dep.groupId.text(),
                artifactId : dep.artifactId.text(),
                version    : dep.version.text()
        ]
    }

    return project
}

def projects = poms.collect { parsePom(it) }

// ---------------- Knoten und Kanten sammeln ----------------
def allNodes = [] as Set
def edges = []

projects.each { p ->
    def projId = "${p.groupId}:${p.artifactId}:${p.version}"

    // Projekt nur, wenn gefiltert
    if (!p.groupId.startsWith(groupFilter)) return
    allNodes << projId

    if (p.parent && p.parent.groupId.startsWith(groupFilter)) {
        def parentId = "${p.parent.groupId}:${p.parent.artifactId}:${p.parent.version}"
        allNodes << parentId
        edges << [type:"parent", source:projId, target:parentId]
    }

    p.dependencies.findAll { it.groupId.startsWith(groupFilter) }.each { d ->
        def depId = "${d.groupId}:${d.artifactId}:${d.version}"
        allNodes << depId
        edges << [type:"dependency", source:projId, target:depId]
    }

    p.imports.findAll { it.groupId.startsWith(groupFilter) }.each { imp ->
        def impId = "${imp.groupId}:${imp.artifactId}:${imp.version}"
        allNodes << impId
        edges << [type:"import", source:projId, target:impId]
    }
}

// ---------------- GraphML erzeugen ----------------
def writer = new StringWriter()
def xml = new MarkupBuilder(writer)

xml.graphml(
        xmlns:"http://graphml.graphdrawing.org/xmlns",
        "xmlns:xsi":"http://www.w3.org/2001/XMLSchema-instance",
        "xmlns:y":"http://www.yworks.com/xml/graphml",
        "xsi:schemaLocation":"http://graphml.graphdrawing.org/xmlns http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd") {

    graph(id:"G", edgedefault:"directed") {
        // Knoten einmalig
        allNodes.each { id ->
            node(id: id) {
                data(key:"d0") {
                    "y:ShapeNode" {
                        "y:NodeLabel"(id)
                        "y:Shape"(type:"rectangle")
                    }
                }
            }
        }

        // Kanten
        def edgeCount = 0
        edges.each { e ->
            edge(id:"e${edgeCount++}", source:e.source, target:e.target) {
                data(key:"d1") {
                    "y:PolyLineEdge" {
                        if (e.type == "parent") {
                            "y:LineStyle"(color:"#0000FF", type:"line", width:"2.0")
                        } else if (e.type == "dependency") {
                            "y:LineStyle"(color:"#008000", type:"line", width:"1.0")
                        } else if (e.type == "import") {
                            "y:LineStyle"(color:"#FFA500", type:"dashed", width:"2.0")
                        }
                        "y:Arrows"(source:"none", target:"standard")
                    }
                }
            }
        }
    }
}

// ---------------- Datei speichern ----------------
def outputFile = new File("filtered-project-graph.graphml")
outputFile.text = writer.toString()
println "GraphML-Datei erstellt: ${outputFile.absolutePath}"
