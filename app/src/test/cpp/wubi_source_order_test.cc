#include "wubi_source_order.h"

#include <cassert>
#include <sstream>
#include <string>
#include <unordered_map>

int main() {
  std::istringstream input(
      "---\n"
      "name: wubi86\n"
      "...\n"
      "发\tv\n"
      "发\tntc\n"
      "发\tntcy\n"
      "囗\tlhn\n"
      "囗\tlhng\n"
      "囗\tcopp\n"
      "坏\ta1\n");
  std::unordered_map<std::string, std::string> codes;
  xime::LoadWubiSourceCodes(input, &codes);
  assert(codes.at("发") == "ntcy");
  assert(codes.at("囗") == "lhng");
  assert(codes.find("坏") == codes.end());
  assert(xime::NormalizeWubiCode("NTCY") == "ntcy");
  return 0;
}
