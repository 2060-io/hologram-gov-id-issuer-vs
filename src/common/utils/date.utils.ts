/**
 * This function formats a date string in the format 'YYMMDD' into 'YYYYMMDD'
 * specifically for birthdates. It assumes the year is either in the 1900s or
 * 2000s, based on the current year, and validates that the date is valid before
 * returning the formatted string.
 *
 * @param dateString - The date in 'YYMMDD' format.
 * @returns The date in 'YYYYMMDD' format if valid, otherwise the original string.
 */
export function formatBirthDate(dateString: string): string {
  if (dateString.length !== 6) return dateString

  const year = parseInt(dateString.substring(0, 2))
  const month = parseInt(dateString.substring(2, 4))
  const day = parseInt(dateString.substring(4, 6))

  const currentYear = new Date().getFullYear() % 100
  const fullYear = year <= currentYear ? 2000 + year : 1900 + year

  const date = new Date(fullYear, month - 1, day)

  if (date.getMonth() + 1 !== month) return dateString

  return `${fullYear}${month.toString().padStart(2, '0')}${day.toString().padStart(2, '0')}`
}
