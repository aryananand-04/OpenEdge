# TypeScript Web Viewer

This directory contains the TypeScript web component for displaying processed frames.

## Setup

1. Install dependencies:
```bash
cd web
npm install
```

2. Build TypeScript:
```bash
npm run build
```

3. The compiled JavaScript will be in `dist/` directory

## Integration with Android

The WebViewActivity can load the HTML file from assets or a local server.

## Project Structure

```
web/
├── src/
│   ├── viewer.ts          # Main frame viewer class
│   └── types.ts           # TypeScript type definitions
├── public/
│   ├── index.html         # HTML page
│   └── styles.css         # Styling
├── tsconfig.json          # TypeScript configuration
├── package.json           # Dependencies
└── README.md              # This file
```

## Features

- Frame display on canvas
- FPS counter
- Resolution display
- Frame statistics

