#include "util.h"
#include <sodium.h>
#include <string>
#include <vector>

namespace hyponome {
  namespace util {

    std::string bin2hex(std::vector<unsigned char> binary) {
      const std::size_t len = binary.size();
      std::string out(len * 2, '\0');
      sodium_bin2hex(&out[0], len * 2 + 1, &binary[0], len);
      return out;
    }

  }
}
