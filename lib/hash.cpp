#include "hash.h"
#include <sodium.h>
#include <string>
#include <vector>

namespace hyponome {
  namespace hash {

    std::vector<unsigned char> sha256(std::string str) {
      std::vector<unsigned char> hash(crypto_hash_sha256_BYTES);
      auto msg = reinterpret_cast<const unsigned char *>(str.c_str());
      crypto_hash_sha256(&hash[0], msg, str.length());
      return hash;
    }

  }
}
