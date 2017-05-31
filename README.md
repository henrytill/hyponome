![](doc/molluscs.gif)

hyponome
========

[![Build Status](https://travis-ci.org/henrytill/hyponome.svg?branch=master)](https://travis-ci.org/henrytill/hyponome)

A [content-addressable](https://en.wikipedia.org/wiki/Content-addressable_storage) file store.

## Rationale

hyponome started life as a simple paste server, with the premise that URLs of uploaded files should be discoverable offline using standard tools (originally `git hash-object`, currently `sha256sum` or `shasum -a 256`).  The security model of this approach is clearly limited, but seemed appropriate for the use-case of sharing files in a semi-public manner.  It has since evolved to become a generic immutable file store, intended for use within a local network.

## Status

In its current state, hyponome should be considered experimental and unstable.

## History

* Fall 2015: An [early version](https://bitbucket.org/henrytill/hyponome-clojure) is written in Clojure.
* Winter 2016: The [next version](https://github.com/henrytill/hyponome/tree/akka-version) is written in Scala using the [Akka](http://akka.io) framework.
* Spring 2016: [Another version](https://github.com/henrytill/hyponome/tree/http4s-rewrite) is written in Scala, removing Akka in favor of a [Scalaz](https://github.com/scalaz/scalaz) `Task`-based implementation.
* Spring 2017: Development of a new version is underway.

## Inspiration:

* Joe Armstrong's ["The Mess We're In"](https://www.youtube.com/watch?v=lKXe3HUG2l4) and ["The web of names, hashes and UUIDs"](http://joearms.github.io/2015/03/12/The_web_of_names.html)
* [Tahoe-LAFS](https://tahoe-lafs.org)
* Plan 9's [Venti](http://doc.cat-v.org/plan_9/4th_edition/papers/venti/)
* [Git](http://git-scm.com/)
* [sprunge](http://sprunge.us/)
