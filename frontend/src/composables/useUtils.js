export function formatScore(score) {
  return score == null ? 'n/a' : Number(score).toFixed(4)
}

export function formatDate(value) {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

export function extractError(error) {
  const response = error?.response?.data
  if (response?.details?.length) {
    return response.details.join('；')
  }
  return response?.error || error?.message || '请求失败'
}
