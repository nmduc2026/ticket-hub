import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'

import { createQueryClient } from '@/app/query-client'
import { server } from '@/test/server'

import { ImportExcelDialog } from './ImportExcelDialog'

const BASE = { id: '0199a0a0-0000-7000-8000-000000000001', dryRun: true, totalRows: 3, validRows: 2, errorRows: 1, created: 2, updated: 0, skipped: 0 }

function renderDialog(onImported = vi.fn()) {
  render(
    <QueryClientProvider client={createQueryClient()}>
      <ImportExcelDialog open onOpenChange={() => {}} type="venues" onImported={onImported} />
    </QueryClientProvider>,
  )
  return onImported
}

describe('Modal import Excel', () => {
  it('chọn file → kiểm tra hiện tổng / hợp lệ / lỗi theo dòng–cột → import ghi dòng hợp lệ và báo kết quả', async () => {
    const calls: string[] = []
    server.use(
      http.post('/api/v1/admin/imports/venues', ({ request }) => {
        const url = new URL(request.url)
        calls.push(`${url.searchParams.get('dryRun')}:${url.searchParams.get('onDuplicate')}`)
        return url.searchParams.get('dryRun') === 'true'
          ? HttpResponse.json({ ...BASE, status: 'INVALID', errors: [{ row: 4, column: 'ma_xa', message: 'Mã xã / phường không tồn tại' }], hasErrorFile: true })
          : HttpResponse.json({ ...BASE, dryRun: false, status: 'IMPORTED', errors: [], hasErrorFile: true })
      }),
    )
    const user = userEvent.setup()
    const onImported = renderDialog()

    expect(screen.getByRole('button', { name: 'Kiểm tra' })).toBeDisabled()
    await user.upload(screen.getByLabelText(/Chọn file/), new File(['x'], 'dia-diem.xlsx'))
    await user.click(screen.getByLabelText('Bỏ qua'))
    await user.click(screen.getByRole('button', { name: 'Kiểm tra' }))

    expect(await screen.findByText('Mã xã / phường không tồn tại')).toBeInTheDocument()
    expect(screen.getByText('ma_xa')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tải file lỗi' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Import' }))
    expect(await screen.findByText('Thêm mới')).toBeInTheDocument()
    expect(calls).toEqual(['true:SKIP', 'false:SKIP'])
    await waitFor(() => expect(onImported).toHaveBeenCalledTimes(1))
  })

  it('file không có dòng hợp lệ thì không cho import', async () => {
    server.use(
      http.post('/api/v1/admin/imports/venues', () =>
        HttpResponse.json({ ...BASE, status: 'INVALID', validRows: 0, errorRows: 3, created: 0, errors: [{ row: 2, column: 'ten', message: 'Thiếu tên' }], hasErrorFile: true }),
      ),
    )
    const user = userEvent.setup()
    renderDialog()
    await user.upload(screen.getByLabelText(/Chọn file/), new File(['x'], 'dia-diem.xlsx'))
    await user.click(screen.getByRole('button', { name: 'Kiểm tra' }))

    expect(await screen.findByText('Thiếu tên')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Import' })).toBeDisabled()
  })

  it('lỗi từ server (file sai định dạng) hiện thông báo tiếng Việt', async () => {
    server.use(
      http.post('/api/v1/admin/imports/venues', () =>
        HttpResponse.json({ type: 'about:blank', title: 'x', status: 422, code: 'IMPORT_FILE_INVALID' }, { status: 422, headers: { 'Content-Type': 'application/problem+json' } }),
      ),
    )
    const user = userEvent.setup()
    renderDialog()
    await user.upload(screen.getByLabelText(/Chọn file/), new File(['x'], 'sai.xlsx'))
    await user.click(screen.getByRole('button', { name: 'Kiểm tra' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('File không đọc được hoặc thiếu cột')
  })
})
