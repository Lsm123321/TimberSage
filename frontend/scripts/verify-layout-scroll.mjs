/**
 * Compare document vs .layout-v2 .content scroll metrics (stub login via localStorage).
 * Run: node scripts/verify-layout-scroll.mjs  (vite dev server on :5173)
 */
import { chromium } from 'playwright'

const base = process.env.VITE_DEV_URL || 'http://127.0.0.1:5173'

async function measure(path) {
  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({
    viewport: { width: 1280, height: 720 },
  })
  await context.addInitScript(() => {
    localStorage.setItem('token', '__playwright_stub_token__')
  })
  const page = await context.newPage()
  await page.goto(`${base}${path}`, { waitUntil: 'networkidle', timeout: 90000 })
  await page.waitForTimeout(2500)
  const finalUrl = page.url()

  const metrics = await page.evaluate(() => {
    const content = document.querySelector('.layout-v2 .content')
    const layout = document.querySelector('.layout-v2')
    return {
      pathname: location.pathname,
      documentElement: {
        scrollHeight: document.documentElement.scrollHeight,
        clientHeight: document.documentElement.clientHeight,
      },
      bodyScrollHeight: document.body.scrollHeight,
      innerHeight: window.innerHeight,
      layoutV2: layout
        ? {
            scrollHeight: layout.scrollHeight,
            clientHeight: layout.clientHeight,
          }
        : null,
      mainContent: content
        ? {
            scrollHeight: content.scrollHeight,
            clientHeight: content.clientHeight,
          }
        : null,
    }
  })

  await browser.close()
  return { requestedPath: path, finalUrl, metrics }
}

const paths = ['/', '/buildings']

console.log(`Base URL: ${base}\n`)

for (const p of paths) {
  const r = await measure(p)
  const { metrics } = r
  const docOverflow =
    metrics.documentElement.scrollHeight - metrics.documentElement.clientHeight
  const contentOverflow = metrics.mainContent
    ? metrics.mainContent.scrollHeight - metrics.mainContent.clientHeight
    : null

  console.log(`--- ${p} ---`)
  console.log(`finalUrl: ${r.finalUrl}`)
  console.log(`pathname: ${metrics.pathname}`)
  console.log(`document overflow (doc.scrollHeight - clientHeight): ${docOverflow}px (expect ~0)`)
  console.log(
    `.content overflow: ${contentOverflow === null ? 'N/A (no .layout-v2 .content)' : `${contentOverflow}px`}`
  )
  console.log(`layout-v2: ${JSON.stringify(metrics.layoutV2)}`)
  console.log('')
}
