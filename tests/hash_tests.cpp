#include "catch.hpp"
#include <exception>
#include <fstream>
#include <iostream>
#include <sodium.h>
#include <vector>

std::vector<unsigned char> sha256(std::string str) {
  std::vector<unsigned char> hash(crypto_hash_sha256_BYTES);
  auto msg = reinterpret_cast<const unsigned char *>(str.c_str());
  crypto_hash_sha256(&hash[0], msg, str.length());
  return hash;
}

std::string bin2hex(std::vector<unsigned char> binary) {
  const std::size_t len = binary.size();
  std::string out(len * 2, '\0');
  sodium_bin2hex(&out[0], len * 2 + 1, &binary[0], len);
  return out;
}

TEST_CASE("Round-trip a string to/from the filesystem as binary",
          "[crypto_hash_sha256]") {

  // clang-format off
  const std::string expected      = "This is a test file.\n";
  const std::string expected_hash = "649b8b471e7d7bc175eec758a7006ac693c434c8297c07db15286788c837154a";
  // clang-format on

  if (sodium_init() < 0) {
    throw std::runtime_error("sodium_init() failed");
  }

  std::ofstream("test.txt", std::ios::binary) << expected;

  if (std::ifstream is{"test.txt", std::ios::binary | std::ios::ate}) {
    auto size = is.tellg();
    std::string str(size, '\0');
    is.seekg(0);
    if (is.read(&str[0], size)) {
      auto hash = sha256(str);
      auto hash_hex = bin2hex(hash);
      REQUIRE(expected == str);
      REQUIRE(expected_hash == hash_hex);
      return;
    }
  }

  REQUIRE(false);
}
