#ifndef HASH_H
#define HASH_H

#include <string>
#include <vector>

namespace hyponome {
  namespace hash {

    std::vector<unsigned char> sha256(const std::vector<unsigned char> &);

    std::vector<unsigned char> blake2b(const std::vector<unsigned char> &);

    std::vector<unsigned char> blake2b(const std::vector<unsigned char> &,
                                       const std::vector<unsigned char> &);
  }
}

#endif
