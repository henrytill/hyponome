#include "catch.hpp"
#include "hash.h"
#include "util.h"
#include <exception>
#include <fstream>
#include <iostream>
#include <sodium.h>
#include <string>

using namespace hyponome;

std::string round_trip(const std::string &path, const std::string &data) {
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

TEST_CASE("Round-trip a string to/from the filesystem as binary",
          "[crypto_hash_sha256]") {
  if (sodium_init() < 0)
    throw std::runtime_error("sodium_init() failed");

  const std::string expected = "This is a test file.\n";
  const std::string expected_hash =
      "649b8b471e7d7bc175eec758a7006ac693c434c8297c07db15286788c837154a";

  std::string str = round_trip("test.txt", expected);
  auto hash = hash::sha256(str);
  auto hash_hex = util::bin2hex(hash);
  REQUIRE(expected == str);
  REQUIRE(expected_hash == hash_hex);
}
