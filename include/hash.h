#ifndef HASH_H
#define HASH_H

#include <string>
#include <vector>

namespace hyponome {
  namespace hash {

    std::vector<unsigned char> sha256(std::string);

  }
}

#endif
