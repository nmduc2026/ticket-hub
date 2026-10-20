export const ROLES = ['CUSTOMER', 'ORGANIZER', 'ADMIN'] as const

export type Role = (typeof ROLES)[number]

export function hasRole(roles: readonly string[], role: Role): boolean {
  return roles.includes(role)
}
