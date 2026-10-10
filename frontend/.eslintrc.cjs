module.exports = {
  root: true,
  env: {
    browser: true,
    node: true,
    es2022: true
  },
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module'
  },
  extends: [],
  plugins: ['vue'],
  overrides: [
    {
      files: ['*.vue'],
      parser: 'vue-eslint-parser',
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module'
      }
    }
  ],
  rules: {
    'no-debugger': 'warn',
    'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }]
  }
}
