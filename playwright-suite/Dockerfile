# Use official Playwright image pre-baked with browsers and OS dependencies
FROM mcr.microsoft.com/playwright:v1.42.0-jammy

WORKDIR /app

# Copy package descriptors
COPY package*.json tsconfig.json playwright.config.ts ./

# Install npm dependencies
RUN npm ci

# Copy tests and worker code
COPY tests/ ./tests/
COPY worker-agent.ts ./


# Run the TypeScript worker agent via ts-node
CMD ["npm", "run", "worker"]
