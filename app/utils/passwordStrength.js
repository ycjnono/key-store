/**
 * 密码强度评估（与 Java 版逻辑一致）
 */

const LEVELS = {
  WEAK: { label: '极弱', color: '#e74c3c' },
  FAIR: { label: '弱', color: '#e67e22' },
  GOOD: { label: '中等', color: '#f1c40f' },
  STRONG: { label: '强', color: '#2ecc71' },
  VERY_STRONG: { label: '极强', color: '#27ae60' },
};

/**
 * @param {string} password
 * @returns {{ label: string, color: string }}
 */
function evaluate(password) {
  if (!password) return LEVELS.WEAK;

  let score = 0;
  const length = password.length;
  if (length >= 8) score++;
  if (length >= 12) score++;
  if (length >= 16) score++;

  let hasLower = false, hasUpper = false, hasDigit = false, hasSpecial = false;
  for (const c of password) {
    if (/[a-z]/.test(c)) hasLower = true;
    else if (/[A-Z]/.test(c)) hasUpper = true;
    else if (/\d/.test(c)) hasDigit = true;
    else hasSpecial = true;
  }
  if (hasLower) score++;
  if (hasUpper) score++;
  if (hasDigit) score++;
  if (hasSpecial) score++;

  if (score <= 2) return LEVELS.WEAK;
  if (score <= 3) return LEVELS.FAIR;
  if (score <= 4) return LEVELS.GOOD;
  if (score <= 5) return LEVELS.STRONG;
  return LEVELS.VERY_STRONG;
}

module.exports = { evaluate };
