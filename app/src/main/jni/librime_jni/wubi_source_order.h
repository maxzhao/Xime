#ifndef XIME_WUBI_SOURCE_ORDER_H_
#define XIME_WUBI_SOURCE_ORDER_H_

#include <cctype>
#include <istream>
#include <string>
#include <unordered_map>

namespace xime {

inline std::string NormalizeWubiCode(const std::string& code) {
  std::string normalized;
  normalized.reserve(code.size());
  for (unsigned char ch : code) {
    if (!std::isalpha(ch)) return "";
    normalized.push_back(static_cast<char>(std::tolower(ch)));
  }
  return normalized;
}

// Loads forward dictionary entries in source order. A longer code replaces a
// shorter one; equal-length codes keep the first source occurrence.
inline void LoadWubiSourceCodes(
    std::istream& input,
    std::unordered_map<std::string, std::string>* codes) {
  if (!codes) return;
  bool in_body = false;
  std::string line;
  while (std::getline(input, line)) {
    if (!line.empty() && line.back() == '\r') line.pop_back();
    if (!in_body) {
      if (line == "...") in_body = true;
      continue;
    }
    if (line.empty() || line.front() == '#') continue;
    const size_t first_tab = line.find('\t');
    if (first_tab == std::string::npos || first_tab == 0) continue;
    const size_t second_tab = line.find('\t', first_tab + 1);
    const std::string text = line.substr(0, first_tab);
    const std::string raw_code = line.substr(
        first_tab + 1,
        second_tab == std::string::npos ? std::string::npos
                                        : second_tab - first_tab - 1);
    const std::string code = NormalizeWubiCode(raw_code);
    if (code.empty()) continue;
    auto [it, inserted] = codes->emplace(text, code);
    if (!inserted && code.size() > it->second.size()) {
      it->second = code;
    }
  }
}

}  // namespace xime

#endif  // XIME_WUBI_SOURCE_ORDER_H_
