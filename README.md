# REOCCA
Test/loadtest recorder/replayer.
Built on top of spray (spray.io), written in Scala.

A typical use case for REOCCA would be to serve mock responses to an application under load test. REOCCA is a Spray microservice that 
* serves those mock responses when in 'replay' mode
* forwards to configured URLs when in 'forward' mode
* caches the response returned by a forward in 'record' mode
* caches can be imported, exported or edited
* testers can specify a delay time per response
* different testers can share a single REOCCA service and autonomously manage their own cache, (aka cache target)

## How to run
Assuming Scala and Sbt are installed and the git repo is cloned, at the root directory startup SBT
```
    sbt -Dservice.port=9999
```
and inside the SBT console enter
```
    run
```    
This starts REOCCA on your localhost at port 9999.
Now you can load a cache via a rest `PUT` with header `Content-Type` = `application/json; charset=UTF-8` on
```
    http://localhost:9999/REOCCA/myexample
```
    
Here is an example of a cache to post:
```javascript
        [  { "target" : {
            "name" : "/todos/toforward",
            "replay" : true, "forward" : true, "record" : true,
            "minSimDelay" : 1000, "maxSimDelay" : 2500,
            "keyGeneratorName" : "default",
            "keyGeneratorParameter" : "pathFilter{1}",
            "url" : "http://localhost:8889/one/todos/urgent",
            "filterNamespace" : true,
            "skipHttpHeaders" : true,
            "entries" : [
             {    "key" : "",
                  "method" : "get", 
                  "keyRequestHeader" : [
                        {   "Content-Type" : "application/json"},
                        {   "Accept-encoding" : "UTF-8"}
                  ],
                  "requestHeader" : [                {"Accept":"*/*"}
                  ],
                  "responseHeader" : [
                        {   "Content-Type" : "application/json"},
                  ],
                  "response" : {"objective" : "this is just a simple task"}
             },
             {    "key" : "inprogress",
                  "method" : "get", "requestHeader" : "tbd", "responseHeader" : "tbd",
                  "response" : {"objective" : "get this working"}
             },
             {   "key" : "/inprogress",
                  "method" : "get", "requestHeader" : "tbd", "responseHeader" : "tbd",
                  "response" : {"objective" : "get this working late"}
             }      ]
          }}]
```
Studying the structure of this json should give you a good overview of REOCCA's possibilities.
Note, however that some keywords refer to functionality which is not working at the time of writing. The open issue list shows what is still to come.

If you now do a `GET` on
    `http://localhost:9999/myexample/todos/toforward`
the response will be
```
    {"objective" : "this is just a simple task"}
```
Notice how the path after REOCCA in the `PUT` request becomes the name of the cache target, and the context root via which to reach your cache entries.
While this is still in beta (or alpha?), if REOCCA receives a request it can not handle, it will return the *entire* cache (i.e. *all* cache targets).
 


###FAQ
* Is REOCCA bound to port 9999?
    * Obviously not ;) Forwarding already works, so it is perfectly feasible to have 2 servers on different ports, where one server forwards to the other. Of course you can also test forwarding with a rest service of your own. Make sure its response is json, and the content-type is according.
* Why Spray and not Akka HTTP?
    * When I started this (Q2-2015) I assumed the maturity of Spray would lead to less surprises, and hence would be the safest route to a result that could demonstrate functionality and scalability. Porting to Akka HTTP in the near future is a viable option.
* Why Scala and not Java 8?
    * I believed that it would be easier and more natural to make use of Spray (itself being written in Scala) using Scala. I believed the *reactive* principles are easier to follow in Scala. My recent experience seems to confirm both.
    
