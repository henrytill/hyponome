![](doc/molluscs.gif)

hyponome
========

hyponome is a [content-addressable](https://en.wikipedia.org/wiki/Content-addressable_storage) file store with a simple REST interface, accessible via HTTP.

## Rationale

hyponome started life as a simple paste server, with the premise that URLs of uploaded files should be discoverable offline using standard tools (originally `git hash-object`, currently `sha256sum` or `shasum -a 256`).  The security model of this approach is clearly limited, but seemed appropriate for the use-case of sharing files in a semi-public manner.  It has since evolved to become a generic immutable file store, intended for use within a local network.

## Status

In its current state, hyponome should be considered experimental and unstable.  Discussion of ongoing development can be found in the [issue tracker](https://github.com/henrytill/hyponome-scala/issues) and the [wiki](https://github.com/henrytill/hyponome-scala/wiki).

## Usage

In order to use hyponome, you will need [a JDK](http://openjdk.java.net/), [Make](https://www.gnu.org/software/make/), and [sbt](http://www.scala-sbt.org/).

```
$ git clone https://github.com/henrytill/hyponome-scala.git

$ cd hyponome-scala

$ make

$ sbt http/run
```

You can also build and run a standalone jar:
```
$ make

$ sbt http/assembly

$ java -cp http/target/scala-2.11/hyponome-assembly-0.1.0-SNAPSHOT.jar hyponome.http.Main
```

Once the server is running:
```
$ cat > hello.txt << EOF
> Hello, world!
> EOF

$ sha256sum hello.txt
d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5  hello.txt

$ curl --cacert <path/to/hyponome.pem> -F file=@hello.txt https://localhost:4000/objects
[
  {
    "name" : "hello.txt",
    "hash" : {
      "SHA256Hash" : "d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5"
    },
    "contentType" : "text/plain; charset=UTF-8",
    "status" : {
      "Created" : {

      }
    },
    "file" : {
      "URI" : "https://localhost:4000/objects/d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5/hello.txt"
    },
    "length" : 14,
    "remoteAddress" : {
      "InetAddress" : "192.168.1.253"
    }
  }
]%

$ curl --cacert <path/to/hyponome.pem> https://localhost:4000/objects/d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5/hello.txt
Hello, world!

$ curl -s --cacert <path/to/hyponome.pem> https://localhost:4000/objects/d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5/hello.txt | sha256sum
d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5  -
```
**NOTE**: `hyponome.pem` is created by running `make` (see above) and is located at `src/test/resources/hyponome.pem`.  It is only intended to be used for development/preview purposes.

## Other Versions

The [original version](https://github.com/henrytill/hyponome-clojure) was written in Clojure.

## Inspiration:

* Joe Armstrong's ["The Mess We're In"](https://www.youtube.com/watch?v=lKXe3HUG2l4) and ["The web of names, hashes and UUIDs"](http://joearms.github.io/2015/03/12/The_web_of_names.html)
* [Tahoe-LAFS](https://tahoe-lafs.org)
* Plan 9's [Venti](http://doc.cat-v.org/plan_9/4th_edition/papers/venti/)
* [Git](http://git-scm.com/)
* [sprunge](http://sprunge.us/)
