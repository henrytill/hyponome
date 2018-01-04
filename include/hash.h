#ifndef HASH_H
#define HASH_H

#include <string>
#include <vector>

namespace hyponome {
  namespace hash {

    std::vector<unsigned char> sha256(std::vector<unsigned char>);

    std::vector<unsigned char> blake2b(std::vector<unsigned char>);

    std::vector<unsigned char> blake2b(std::vector<unsigned char>, std::vector<unsigned char>);

  }
}

#endif
