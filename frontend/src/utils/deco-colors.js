/**
 * 勋章/称号专属配色（icon 名 → 主题色）
 * 商城卡片、个人中心装扮、身份卡佩戴标签三处共用，保证同一商品全站同色。
 */
export const DECO_COLORS = {
  sprout: '#52B788', // 萌芽勋章 · 嫩绿
  feather: '#4A9FE8', // 破茧勋章 · 天空蓝
  mountain: '#E8894A', // 登峰勋章 · 暖橙
  crown: '#E8A93D', // 传奇勋章 · 琥珀金
  'graduation-cap': '#5C7FD9', // 称号·学无止境 · 靛蓝
  lightbulb: '#D95C8A', // 称号·记忆大师 · 玫红
  trophy: '#D95845', // 称号·一词封神 · 正红
  award: '#8AA39B' // 兜底
}

/** 主色 */
export function decoColor(icon) {
  return DECO_COLORS[icon] || DECO_COLORS.award
}

/** 同色浅底（hex 追加 alpha），用于图标圆底 */
export function decoSoftBg(icon) {
  return decoColor(icon) + '1A'
}
