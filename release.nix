{ hyponome ? { outPath = ./.; revCount = 0; shortRev = "abcdef"; rev = "HEAD"; } }:

let
  pkgs = import <nixpkgs> {};

  lib = pkgs.lib;

  version = lib.fileContents ./VERSION;

  jobs = with import ./common.nix { inherit pkgs; }; rec {

    tarball =
      pkgs.releaseTools.sourceTarball {
        inherit version;
        name = "hyponome-tarball";
        src = hyponome;
        nativeBuildInputs = buildDeps;
        buildInputs = deps;
        distTarget = "package_source";
      };

    build =
      pkgs.releaseTools.nixBuild {
        inherit version;
        name = "hyponome";
        src = jobs.tarball;
        nativeBuildInputs = buildDeps;
        buildInputs = deps;
        checkTarget = "test";
      };

    makeDeb =
      system: diskImageFun: extraPackages: extraDebPackages:

      with import <nixpkgs> { inherit system; };

      releaseTools.debBuild {
        inherit version;
        name = "hyponome-deb";
        src = jobs.tarball;
        diskImage = (diskImageFun vmTools.diskImageFuns) {
          extraPackages =
            [ "capnproto"
              "catch"
              "cmake"
              "doxygen"
              "libcapnp-dev"
              "libsodium-dev"
              "pkg-config"
            ] ++ extraPackages;
        };
        patchPhase = ''
          substituteInPlace tests/main.cpp       --replace catch/catch.hpp catch.hpp
          substituteInPlace tests/tests_hash.cpp --replace catch/catch.hpp catch.hpp
          substituteInPlace tests/tests_rpc.cpp  --replace catch/catch.hpp catch.hpp
        '';
        buildPhase = ''
          mkdir build
          cd build
          cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=/usr ../.
          make
        '';
        memSize = 1024;
        debRequires = [ "libcapnp-0.5.3" "libsodium18" ] ++ extraDebPackages;
        debMaintainer = "Henry Till <henrytill@gmail.com>";
        checkTarget = "test";
      };

    makeDeb_x86_64 = makeDeb "x86_64-linux";

    deb_ubuntu1604x86_64 = makeDeb_x86_64 (diskImageFuns: diskImageFuns.ubuntu1604x86_64) [] [];

    release = pkgs.releaseTools.aggregate {
      name = "hyponome-${version}";
      meta.description = "Release-critical builds";
      constituents =
        [ tarball
          build
          deb_ubuntu1604x86_64
        ];
    };
  };
in
  jobs
