import { defineConfig } from 'orval'

// OpenAPI do backend sinh ra (backend/app: OpenApiContractTests ghi file này) là nguồn sự thật cho client.
// Chạy `npm run api:generate` sau khi hợp đồng API đổi; không sửa tay src/api/generated.
const input = './openapi/openapi.json'

export default defineConfig({
  api: {
    input,
    output: {
      mode: 'tags-split',
      target: './src/api/generated/endpoints',
      schemas: './src/api/generated/model',
      client: 'react-query',
      httpClient: 'fetch',
      override: {
        mutator: { path: './src/lib/api-client.ts', name: 'apiFetch' },
        // Orval 7 + fetch truyền AbortSignal vào tham số RequestInit (lỗi kiểu); tắt để code sinh ra qua type-check.
        query: { signal: false },
      },
      // Header khai trong OpenAPI (Idempotency-Key) thành tham số của hàm sinh ra, không truyền tay qua RequestInit.
      headers: true,
    },
  },
  apiZod: {
    input,
    output: {
      mode: 'tags-split',
      target: './src/api/generated/zod',
      client: 'zod',
      fileExtension: '.zod.ts',
    },
  },
})
