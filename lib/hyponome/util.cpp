#include "hyponome/util.h"
#include <sodium.h>
#include <string>
#include <vector>

namespace hyponome {
  namespace util {

    std::string bin2hex(const std::vector<unsigned char> &binary) {
      const std::size_t len = binary.size();
      std::string out(len * 2, '\0');
      sodium_bin2hex(&out[0], len * 2 + 1, &binary[0], len);
      return out;
    }

    std::vector<unsigned char> hex2bin(const std::string &hex) {
      const std::size_t hex_len = hex.length();
      const std::size_t bin_len = hex_len * 0.5;
      std::vector<unsigned char> out(bin_len);
      if (sodium_hex2bin(&out[0], bin_len, &hex[0], hex_len, nullptr, nullptr, nullptr) == 0)
        return out;
      else
        throw CodecError{};
    }
  }
}
