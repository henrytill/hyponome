{ pkgs }:

rec {

  doxygenDeps = with pkgs;
    [ doxygen
      ghostscript
      graphviz
      texlive.combined.scheme-full
    ];

  buildDeps = with pkgs;
    [ catch
      cmake
      pkgconfig
    ] ++ doxygenDeps;

  deps = with pkgs;
    [ capnproto
      libsodium
    ];
}
