#include "catch.hpp"
#include "hash.h"
#include "hasher.capnp.h"
#include "rpc.h"
#include "util.h"
#include <algorithm>
#include <capnp/ez-rpc.h>
#include <capnp/message.h>
#include <iostream>
#include <kj/async.h>
#include <sodium.h>
#include <string>
#include <thread>

using namespace hyponome;

TEST_CASE("Hash a string using the Hasher RPC Service") {
  const std::string expected = "This is a test file.\n";
  const std::string expectedHashHex =
      "649b8b471e7d7bc175eec758a7006ac693c434c8297c07db15286788c837154a";

  auto data =
      capnp::Data::Reader(reinterpret_cast<const unsigned char *>(&expected[0]), expected.length());

  capnp::EzRpcServer server(kj::heap<hyponome::HasherImpl>(), "localhost:5923");
  auto &serverWaitScope = server.getWaitScope();
  auto portPromise = server.getPort();
  auto serverReady = portPromise.wait(serverWaitScope);

  capnp::EzRpcClient client("localhost:5923");
  auto &clientWaitScope = client.getWaitScope();
  auto cap = client.getMain<Hasher>();
  auto request = cap.hashRequest();
  request.setData(data);
  auto promise = request.send();
  auto response = promise.wait(clientWaitScope);

  auto actualHash = response.getHash();
  auto actualHashHex =
      util::bin2hex(std::vector<unsigned char>(actualHash.begin(), actualHash.end()));

  REQUIRE(expectedHashHex == actualHashHex);
}
