{ nixpkgs ? import <nixpkgs> {} }:

let

  inherit (nixpkgs) pkgs;

in pkgs.stdenv.mkDerivation {
  name = "hyponome";
  src = ./.;
  nativeBuildInputs =
    [ pkgs.catch
      pkgs.cmake
      pkgs.doxygen
      pkgs.pkgconfig
    ];
  buildInputs =
    [ pkgs.capnproto
      pkgs.libsodium
    ];
}
