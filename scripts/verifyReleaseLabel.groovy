import groovy.json.*

def verifyReleaseLabel(def value, def user, def password, def url) {

  // the valid labels for bumping version processing
  String[] arrValidLabels = ['release-major', 'release-minor', 'release-patch', 'no-release']

//  println value
//  println user
//  println password
//  println url

  // retrieve label names from pull request
  //process = ["curl", "--user", "PeteSwauger:xxxxxx", "-X", "GET", "-H", "Content-Type: application/json", "https://api.github.com/repos/zowe/zowe-cli-sample-plugin/issues/20/labels"].execute().text
  //process = ["curl", "-X", "GET", "-H", "Content-Type: application/json", "https://api.github.com/repos/zowe/zowe-cli-sample-plugin/issues/20/labels"].execute().text

  def userpassword = "$user" + ":" + "$password"
//  println userpassword
  process = ["curl", "--user", userpassword , "-X", "GET", "-H", "Content-Type: application/json", "$url"].execute().text

  //process = ["curl", "--user", "ws617385:Peter234*", "-X", "GET", "-H", "Content-Type: application/json", "https://github.gwd.broadcom.net/api/v3/repos/ws617385/playground/labels"].execute().text

//  println process

  // pull the label names out 
  def list = []
  def jsonSlurper = new JsonSlurper()
  data = jsonSlurper.parseText(process)

  // loop through the label names and add valid labels to array
  data.each {
    println  it."$value"
    if ( it."$value" in arrValidLabels ) {
      list.add(it."$value")
    }
  }

//  println "list = " + list

  // determine if valid labels found
  // if more than one, throw error
  if (list.size() > 1) {
    println "list size = " + list.size()
  }
  // if none, throw error
  else if (list.size() == 0) {
    println "list is empty"
  }

  if( arrValidLabels[0] in list || arrValidLabels[1] in list || arrValidLabels[2] in list || arrValidLabels[3] in list ){
      println "found"
  }
  else {
      println "not found"
  }
}

// verifyReleaseLabel("name", "ws617385", "Peter234*", "https://github.gwd.broadcom.net/api/v3/repos/ws617385/playground/labels")
verifyReleaseLabel(args[0], args[1], args[2], args[3])