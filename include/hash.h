#ifndef HASH_H
#define HASH_H

#include <string>
#include <vector>

namespace hyponome {
  namespace hash {

    struct Error {};

    std::vector<unsigned char> sha256(const std::vector<unsigned char> &);

    std::vector<unsigned char> blake2b(const std::vector<unsigned char> &);

    std::vector<unsigned char> blake2b(const std::vector<unsigned char> &,
                                       const std::vector<unsigned char> &);
  }
}

#endif

// Local Variables:
// mode: c++
// End:
//
// vim: set filetype=cpp:
