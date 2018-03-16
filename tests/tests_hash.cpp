#include "hyponome/hash.h"
#include "hyponome/util.h"
#include <catch/catch.hpp>
#include <exception>
#include <fstream>
#include <iostream>
#include <sodium.h>
#include <string>

using namespace hyponome;

std::string roundTrip(const std::string &path, const std::string &data) {
  std::ofstream(path, std::ios::binary) << data;
  if (std::ifstream is{path, std::ios::binary | std::ios::ate}) {
    auto size = is.tellg();
    std::string str(size, '\0');
    is.seekg(0);
    if (is.read(&str[0], size)) {
      return str;
    } else {
      throw std::runtime_error("round_trip failed to read from ifstream");
    }
  } else {
    throw std::runtime_error("round_trip failed to open ifstream");
  }
}

TEST_CASE("Hash with SHA256", "[crypto_hash_sha256]") {
  if (sodium_init() < 0)
    throw std::runtime_error("sodium_init() failed");

  const std::string expected = "This is a test file.\n";
  const std::string expectedHashHex =
      "649b8b471e7d7bc175eec758a7006ac693c434c8297c07db15286788c837154a";

  std::vector<unsigned char> msg(expected.length());
  std::transform(expected.begin(), expected.end(), msg.begin(), [](char c) {
    return static_cast<unsigned char>(c);
  });
  auto actualHash = hash::sha256(msg);
  auto actualHashHex = util::bin2hex(actualHash);
  REQUIRE(expectedHashHex == actualHashHex);
}

TEST_CASE("Hash with BLAKE2", "[crypto_generichash]") {
  if (sodium_init() < 0)
    throw std::runtime_error("sodium_init() failed");

  // Source:
  // https://github.com/BLAKE2/BLAKE2/blob/5cbb39c9ef8007f0b63723e3aea06cd0887e36ad/testvectors/blake2b-kat.txt
  const std::string in = "00";
  const std::string key = "000102030405060708090a0b0c0d0e0f10111213141516171819"
                          "1a1b1c1d1e1f202122232425262728292a2b2c2d2e2f30313233"
                          "3435363738393a3b3c3d3e3f";
  const std::string expected = "961f6dd1e4dd30f63901690c512e78e4b45e4742ed197c3"
                               "c5e45c549fd25f2e4187b0bc9fe30492b16b0d0bc4ef9b0"
                               "f34c7003fac09a5ef1532e69430234cebd";

  auto actualHash = hash::blake2b(util::hex2bin(in), util::hex2bin(key));
  auto actualHashHex = util::bin2hex(actualHash);
  REQUIRE(expected == actualHashHex);
}

TEST_CASE("Round-trip a string to/from the filesystem as binary") {
  if (sodium_init() < 0)
    throw std::runtime_error("sodium_init() failed");

  const std::string expected = "This is a test file.\n";
  const std::string expectedHashHex =
      "649b8b471e7d7bc175eec758a7006ac693c434c8297c07db15286788c837154a";

  std::string actual = roundTrip("test.txt", expected);
  std::vector<unsigned char> msg(actual.length());
  std::transform(actual.begin(), actual.end(), msg.begin(), [](char c) {
    return static_cast<unsigned char>(c);
  });
  auto actualHash = hash::sha256(msg);
  auto actualHashHex = util::bin2hex(actualHash);
  REQUIRE(expected == actual);
  REQUIRE(expectedHashHex == actualHashHex);
}
