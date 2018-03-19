let
  pkgs = import <nixpkgs> {};

  env = with import ./common.nix { inherit pkgs; };
    pkgs.stdenv.mkDerivation {
      name = "hyponome-env";
      src = ./.;
      nativeBuildInputs = buildDeps;
      buildInputs = deps;
    };
in
  env
