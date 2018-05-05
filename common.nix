{ pkgs }:

rec {

  texliveEnv = with pkgs; texlive.combine { inherit (texlive) scheme-basic epstopdf; };

  buildDeps = with pkgs;
    [ catch
      cmake
      doxygen
      ghostscript
      pkgconfig
      texliveEnv
    ];

  deps = with pkgs;
    [ capnproto
      libsodium
    ];
}
