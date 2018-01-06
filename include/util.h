#ifndef UTIL_H
#define UTIL_H

#include <string>
#include <vector>

namespace hyponome {
  namespace util {

    std::string bin2hex(const std::vector<unsigned char> &);

    std::vector<unsigned char> hex2bin(const std::string &);
  }
}

#endif

// Local Variables:
// mode: c++
// End:
//
// vim: set filetype=cpp:
