import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'

import { FacetFilter } from './FacetFilter'

const OPTIONS = [
  { value: 'DRAFT', label: 'Nháp' },
  { value: 'ON_SALE', label: 'Đang mở bán' },
  { value: 'ENDED', label: 'Đã kết thúc' },
]

function MultiHarness({ initial = [] }: { initial?: string[] }) {
  const [value, setValue] = useState(initial)
  return <FacetFilter mode="multi" label="Trạng thái" options={OPTIONS} value={value} onChange={setValue} />
}

describe('FacetFilter chọn nhiều', () => {
  it('dòng "Chọn nhiều" đi qua đủ 3 trạng thái: bỏ chọn, một phần, chọn tất cả', async () => {
    const user = userEvent.setup()
    render(<MultiHarness />)
    await user.click(screen.getByRole('button', { name: /Trạng thái/ }))

    const all = screen.getByRole('checkbox', { name: 'Chọn nhiều' })
    expect(all).toHaveAttribute('aria-checked', 'false')

    await user.click(screen.getByRole('checkbox', { name: 'Nháp' }))
    expect(all).toHaveAttribute('aria-checked', 'mixed')

    await user.click(all)
    expect(all).toHaveAttribute('aria-checked', 'true')
    OPTIONS.forEach((option) => expect(screen.getByRole('checkbox', { name: option.label })).toBeChecked())

    await user.click(all)
    expect(all).toHaveAttribute('aria-checked', 'false')
  })

  it('hiện giá trị trong nút; quá 2 giá trị thì hiện "3 đã chọn"', async () => {
    const user = userEvent.setup()
    render(<MultiHarness initial={['DRAFT', 'ON_SALE']} />)
    const trigger = screen.getByRole('button', { name: /Trạng thái/ })
    expect(trigger).toHaveTextContent('Nháp, Đang mở bán')

    await user.click(trigger)
    await user.click(screen.getByRole('checkbox', { name: 'Đã kết thúc' }))
    expect(trigger).toHaveTextContent('3 đã chọn')
  })
})

describe('FacetFilter chọn một', () => {
  it('có "Tất cả" và trả undefined khi chọn lại "Tất cả"', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<FacetFilter mode="single" label="Địa điểm" options={OPTIONS} value="DRAFT" onChange={onChange} />)
    await user.click(screen.getByRole('button', { name: /Địa điểm/ }))

    expect(screen.getByRole('radio', { name: 'Nháp' })).toBeChecked()
    await user.click(screen.getByRole('radio', { name: 'Tất cả' }))
    expect(onChange).toHaveBeenCalledWith(undefined)
  })

  it('danh sách trên 7 lựa chọn có ô tìm và báo "Không có kết quả"', async () => {
    const user = userEvent.setup()
    const many = Array.from({ length: 8 }, (_, index) => ({ value: `v${index}`, label: `Địa điểm ${index}` }))
    render(<FacetFilter mode="single" label="Địa điểm" options={many} value={undefined} onChange={() => {}} />)
    await user.click(screen.getByRole('button', { name: /Địa điểm/ }))

    await user.type(screen.getByRole('textbox', { name: 'Địa điểm' }), 'zzz')
    expect(screen.getByText('Không có kết quả')).toBeInTheDocument()
  })
})
