import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { vi } from 'vitest'

import { SeatMap, type SeatMapSnapshot } from './SeatMap'

// Canvas không chạy trong jsdom; component test kiểm lớp bàn phím / aria-live, là cùng đường `activate` với click
// trên canvas. Hiển thị canvas được kiểm bằng Playwright trên trình duyệt thật.
vi.mock('react-konva', () => {
  const Passthrough = ({ children }: { children?: React.ReactNode }) => <>{children}</>
  const Nothing = () => null
  return { Stage: Passthrough, Layer: Passthrough, Group: Passthrough, Rect: Nothing, Line: Nothing, Text: Nothing }
})

function snapshot(overrides: Partial<SeatMapSnapshot> = {}): SeatMapSnapshot {
  const seats = Array.from({ length: 8 }, (_, index) => ({
    id: `s${index + 1}`,
    sectionCode: 'A',
    row: 'B',
    number: index + 1,
    x: 100 + index * 24,
    y: 200,
    status: 'AVAILABLE',
  }))
  return {
    width: 1200,
    height: 800,
    tiers: [{ id: 't1', name: 'VIP', priceMinor: 2_500_000, currency: 'VND', available: 8 }],
    sections: [
      { code: 'A', name: 'Khu A', shape: {}, tierId: 't1' },
      { code: 'Z', name: 'Khu Z', shape: {} },
    ],
    elements: [],
    seats,
    ...overrides,
  }
}

function Harness({
  data,
  max = 6,
  onLimitReached = () => {},
  onChange = () => {},
}: {
  data: SeatMapSnapshot
  max?: number
  onLimitReached?: () => void
  onChange?: (ids: Set<string>) => void
}) {
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set())
  return (
    <>
      <SeatMap
        snapshot={data}
        selectedIds={selected}
        maxSelect={max}
        onSelectionChange={(next) => {
          setSelected(next)
          onChange(next)
        }}
        onLimitReached={onLimitReached}
      />
      <p data-testid="selected">{[...selected].sort().join(',')}</p>
    </>
  )
}

async function focusMap(user: ReturnType<typeof userEvent.setup>) {
  await user.tab()
  expect(screen.getByRole('application', { name: 'Sơ đồ ghế' })).toHaveFocus()
}

describe('SeatMap: chọn / bỏ chọn bằng bàn phím', () => {
  it('Enter chọn rồi bỏ chọn ghế đang focus, và đọc trạng thái qua aria-live', async () => {
    const user = userEvent.setup()
    render(<Harness data={snapshot()} />)
    await focusMap(user)

    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('status')).toHaveTextContent('Hàng B, ghế 1, VIP, 2.500.000 ₫, còn trống')

    await user.keyboard('{Enter}')
    expect(screen.getByTestId('selected')).toHaveTextContent('s1')
    expect(screen.getByRole('status')).toHaveTextContent('đã chọn')

    await user.keyboard('{Enter}')
    expect(screen.getByTestId('selected')).toHaveTextContent('')
    expect(screen.getByRole('status')).toHaveTextContent('Đã bỏ chọn Hàng B, ghế 1')
  })

  it('chặn ghế thứ 7: không thêm vào lựa chọn và báo giới hạn (BR-02)', async () => {
    const user = userEvent.setup()
    const onLimitReached = vi.fn()
    render(<Harness data={snapshot()} onLimitReached={onLimitReached} />)
    await focusMap(user)

    await user.keyboard('{Home}')
    for (let seat = 1; seat <= 6; seat += 1) {
      await user.keyboard('{Enter}{ArrowRight}')
    }
    expect(screen.getByTestId('selected')).toHaveTextContent('s1,s2,s3,s4,s5,s6')
    expect(onLimitReached).not.toHaveBeenCalled()

    await user.keyboard('{Enter}')
    expect(onLimitReached).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('selected')).toHaveTextContent('s1,s2,s3,s4,s5,s6')
    expect(screen.getByRole('status')).toHaveTextContent('Tối đa 6 ghế mỗi lượt')
  })

  it('bỏ chọn một ghế rồi chọn được ghế khác khi đang đầy giới hạn', async () => {
    const user = userEvent.setup()
    render(<Harness data={snapshot()} max={1} />)
    await focusMap(user)

    await user.keyboard('{Home}{Enter}')
    expect(screen.getByTestId('selected')).toHaveTextContent('s1')
    await user.keyboard('{Enter}{ArrowRight}{Enter}')
    expect(screen.getByTestId('selected')).toHaveTextContent('s2')
  })

  it('ghế đã bán, đang giữ hoặc không bán không chọn được và được đọc rõ lý do', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    const data = snapshot()
    data.seats[0].status = 'SOLD'
    data.seats[1].status = 'HELD'
    data.seats[2].status = 'NOT_FOR_SALE'
    render(<Harness data={data} onChange={onChange} />)
    await focusMap(user)

    await user.keyboard('{Home}{Enter}')
    expect(screen.getByRole('status')).toHaveTextContent('đã bán. Không chọn được.')
    await user.keyboard('{ArrowRight}{Enter}')
    expect(screen.getByRole('status')).toHaveTextContent('đã bán. Không chọn được.')
    await user.keyboard('{ArrowRight}{Enter}')
    expect(screen.getByRole('status')).toHaveTextContent('không bán. Không chọn được.')
    expect(onChange).not.toHaveBeenCalled()
  })

  it('ghế thuộc khu không gán hạng vé là không bán, không chọn được', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    const data = snapshot()
    data.seats[0].sectionCode = 'Z'
    render(<Harness data={data} onChange={onChange} />)
    await focusMap(user)

    await user.keyboard('{Home}{Enter}')
    expect(screen.getByRole('status')).toHaveTextContent('không bán. Không chọn được.')
    expect(onChange).not.toHaveBeenCalled()
  })

  it('có nút phóng to, thu nhỏ và về khung nhìn ban đầu cho người không dùng chuột kéo', () => {
    render(<Harness data={snapshot()} />)
    expect(screen.getByRole('button', { name: 'Phóng to' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Thu nhỏ' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Về khung nhìn ban đầu' })).toBeInTheDocument()
  })
})
