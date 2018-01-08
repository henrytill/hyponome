#ifndef HYPONOME_HASH_H_
#define HYPONOME_HASH_H_

#include <string>
#include <vector>

namespace hyponome {

  ///
  /// \namespace hyponome::hash
  /// \brief Hashing functions
  ///
  namespace hash {

    struct Error {};

    ///
    /// Creates a hash from the given input using SHA256.
    ///
    /// \param msg Data to hash
    /// \returns Hash of the data
    ///
    /// \throws Error
    ///
    std::vector<unsigned char> sha256(const std::vector<unsigned char> &msg);

    ///
    /// Creates a hash from the given input using BLAKE2b.
    ///
    /// \param msg Data to hash
    /// \returns Hash of the data
    ///
    /// \throws Error
    ///
    std::vector<unsigned char> blake2b(const std::vector<unsigned char> &msg);

    ///
    /// Creates a hash from the given input and key using BLAKE2b.
    ///
    /// \param msg Data to hash
    /// \param key Key for hashing
    /// \returns Keyed hash of the data
    ///
    /// \throws Error
    ///
    std::vector<unsigned char> blake2b(const std::vector<unsigned char> &msg,
                                       const std::vector<unsigned char> &key);
  }
}

#endif

// Local Variables:
// mode: c++
// End:
//
// vim: set filetype=cpp:
