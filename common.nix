{ pkgs }:

rec {

  buildDeps = with pkgs;
    [ catch
      cmake
      doxygen
      pkgconfig
    ];

  deps = with pkgs;
    [ capnproto
      libsodium
    ];
}
