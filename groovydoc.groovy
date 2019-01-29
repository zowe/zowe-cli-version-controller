def ant = new AntBuilder()
ant.taskdef(name: "groovydoc", classname: "org.codehaus.groovy.ant.Groovydoc")
ant.groovydoc(
        destdir      : "docs/groovydoc",
        sourcepath   : "src",
        packagenames : "org.zowe.*",
        use          : "true",
        private      : "true") {
    link(packages:"java.,org.xml.,javax.,org.xml.",href:"http://docs.oracle.com/javase/8/docs/api/")
    link(packages:"groovy.,org.codehaus.groovy.",  href:"http://docs.groovy-lang.org/latest/html/api/")
    link(packages:"org.apache.tools.ant.",         href:"http://docs.groovy-lang.org/docs/ant/api/")
    link(packages:"org.junit.,junit.framework.",   href:"http://junit.org/junit4/javadoc/latest/")
    link(packages:"org.codehaus.gmaven.",          href:"http://groovy.github.io/gmaven/apidocs/")
    link(packages:"hudson.,jenkins.",              href:"https://javadoc.jenkins-ci.org/")
}