import fixture from '../../../../shared-test-data/generate-seats.json'
import { generateSeats, type GeneratedSeat, type GridParams, rowLabel } from './generateSeats'

interface Case {
  name: string
  params: GridParams
  expected: GeneratedSeat[]
}

// Cùng file với bản Java (`SeatGeneratorTests`): hai bên phải ra cùng toạ độ (ADR 0030).
const { cases } = fixture as { cases: Case[] }

describe('generateSeats', () => {
  it.each(cases.map((item) => [item.name, item] as const))('%s', (_name, item) => {
    expect(generateSeats(item.params)).toEqual(item.expected)
  })

  it('nhãn hàng đi A…Z rồi AA, AB', () => {
    expect([rowLabel(0), rowLabel(25), rowLabel(26), rowLabel(27), rowLabel(701), rowLabel(702)]).toEqual(['A', 'Z', 'AA', 'AB', 'ZZ', 'AAA'])
  })
})
