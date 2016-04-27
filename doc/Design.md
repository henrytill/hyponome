Design
======

### Inspiration:

* Joe Armstrong's ["The Mess We're In"](https://www.youtube.com/watch?v=lKXe3HUG2l4)
  and ["The web of names, hashes and UUIDs"](http://joearms.github.io/2015/03/12/The_web_of_names.html)
* [Tahoe-LAFS](https://tahoe-lafs.org)
* Plan 9's [Venti](http://doc.cat-v.org/plan_9/4th_edition/papers/venti/)
* [Git](http://git-scm.com/)

### Background:

**hyponome** started life as a simple paste server.  The basic idea was that the URLs of uploaded files should be discoverable offline using standard tools (originally `git hash-object`, currently `sha256sum` or `shasum -a 256`).  The security model of this approach is clearly limited, but seemed appropriate for the use-case of sharing files in a semi-public manner.  However, this part of the design is likely to change in order to expand the range of possible use-cases.

The [original version](https://github.com/henrytill/hyponome-clojure) was written in Clojure.
