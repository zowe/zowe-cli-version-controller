/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

def ant = new AntBuilder()
ant.taskdef(
        name: "groovydoc",
        classname: "org.codehaus.groovy.ant.Groovydoc"
)
ant.groovydoc(
        destdir      : "docs/groovydoc",
        sourcepath   : "src",
        packagenames : "org.zowe.*",
        use          : "true",
        private      : "true") {
    link(packages:"hudson.,jenkins.",              href:"https://javadoc.jenkins-ci.org/")
    link(packages:"java.,org.xml.,javax.,org.xml.",href:"https://docs.oracle.com/javase/8/docs/api/")
    link(packages:"groovy.,org.codehaus.groovy.",  href:"http://docs.groovy-lang.org/latest/html/api/")
    link(packages:"org.apache.tools.ant.",         href:"http://docs.groovy-lang.org/docs/ant/api/")
    link(packages:"org.junit.,junit.framework.",   href:"https://junit.org/junit4/javadoc/latest/")
    link(packages:"org.codehaus.gmaven.",          href:"https://groovy.github.io/gmaven/apidocs/")
}