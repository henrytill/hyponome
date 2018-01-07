#ifndef UTIL_H
#define UTIL_H

#include <string>
#include <vector>

namespace hyponome {

  ///
  /// \namespace hyponome::util
  /// \brief Utility functions
  ///
  namespace util {

    struct Codec_error {};

    ///
    /// Creates a hexadecimal string from a given binary input.
    ///
    /// \param binary Binary to convert
    ///
    std::string bin2hex(const std::vector<unsigned char> &binary);

    ///
    /// Creates binary from a given hexademical string
    ///
    /// \param hex Hex string
    ///
    /// \throws Codec_error
    ///
    std::vector<unsigned char> hex2bin(const std::string &hex);
  }
}

#endif

// Local Variables:
// mode: c++
// End:
//
// vim: set filetype=cpp:
