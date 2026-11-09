# URL Shortener

A URL shortening service built with Java and Spring Boot. It converts long URLs into compact, unique links and redirects users to the original destination. Short links are stored in MySQL and cached in Redis (with a TTL) so repeated redirects do not hit the database every time.

## Features

- **Shorten** any `http`/`https` URL through a REST API
- **Redirect** short links to the original URL (HTTP 302)
- Unique 7-character short codes (`a-z`, `A-Z`, `0-9`) with a collision check
- **MySQL** storage using Spring Data JPA, with an indexed short code for fast lookups
- **Redis caching with TTL** (cache-aside pattern) to reduce repeated database queries and improve redirect performance
- Input validation and clean JSON error responses (400 / 404)
- If Redis is down, the app keeps working by reading from MySQL
- Unit tests with JUnit 5, Mockito and MockMvc

## Tech Stack

| Area | Technology |
|------|------------|
| Language | Java 17 |
| Framework | Spring Boot 3 (Spring Web, Spring Data JPA, Validation) |
| Database | MySQL 8 |
| Cache | Redis 7 |
| Build tool | Maven |
| Testing | JUnit 5, Mockito, MockMvc |
| Local setup | Docker Compose (MySQL + Redis) |

## How It Works

**Creating a short link**

```
POST /api/shorten ──► validate URL ──► generate unique code ──► save in MySQL ──► cache in Redis ──► return short URL
```

**Opening a short link**

```
GET /{code} ──► Redis has it? ──yes──► 302 redirect
                     │
                     no
                     ▼
               read from MySQL ──► store in Redis (TTL 60 min) ──► 302 redirect
                     │
                  not found ──► 404
```

Each entry in Redis expires after a configurable time (default 60 minutes). Links that are opened often stay fast, and links nobody uses disappear from memory by themselves.

## API

### Shorten a URL

`POST /api/shorten`

```bash
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/some/very/long/path?with=query&params=true"}'
```

Response `201 Created`:

```json
{
  "shortCode": "aB3xYz9",
  "shortUrl": "http://localhost:8080/aB3xYz9",
  "originalUrl": "https://example.com/some/very/long/path?with=query&params=true"
}
```

### Open a short link

`GET /{shortCode}`

```bash
curl -i http://localhost:8080/aB3xYz9
```

```
HTTP/1.1 302
Location: https://example.com/some/very/long/path?with=query&params=true
```

You can also paste the short URL in a browser and it will redirect.

### Errors

| Case | Status | Example body |
|------|--------|--------------|
| Empty `url` | 400 | `{"status":400,"error":"Bad Request","message":"url must not be empty"}` |
| Not a valid http/https URL | 400 | `{"status":400,"error":"Bad Request","message":"URL must start with http:// or https:// and contain a host"}` |
| Unknown short code | 404 | `{"status":404,"error":"Not Found","message":"No URL found for short code 'zzzzzzz'"}` |

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker (for MySQL and Redis), or MySQL and Redis installed locally

### Run locally

```bash
git clone https://github.com/<your-username>/url-shortener.git
cd url-shortener

# 1. start MySQL and Redis
docker compose up -d

# 2. start the application
mvn spring-boot:run
```

The app starts on `http://localhost:8080`. The `url_mappings` table is created automatically on first start.

### Configuration

`src/main/resources/application.properties`

| Property | Default | Meaning |
|----------|---------|---------|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/urlshortener` | MySQL connection (host can be set with `DB_HOST`) |
| `spring.datasource.username` / `password` | `root` / `root` | Set with `DB_USERNAME` / `DB_PASSWORD` |
| `spring.data.redis.host` | `localhost` | Redis host (can be set with `REDIS_HOST`) |
| `app.base-url` | `http://localhost:8080` | Used to build the returned short link |
| `app.cache-ttl-minutes` | `60` | How long a link stays in the Redis cache |
| `app.code-length` | `7` | Length of the short code |

To watch the cache while testing:

```bash
docker exec -it url-shortener-redis redis-cli
> KEYS url:*
> TTL url:aB3xYz9
```

## Running the Tests

```bash
mvn test
```

No database or Redis is needed to run the tests; they are replaced with mocks.

- `UrlServiceTest` - code generation and retry on collision, URL validation, cache hit/miss, cache TTL, fallback to MySQL when Redis is down
- `UrlControllerTest` - request/response format, 302 redirect with `Location` header, 400 and 404 errors

## Project Structure

```
url-shortener/
├── pom.xml
├── docker-compose.yml
├── README.md
└── src
    ├── main
    │   ├── java/com/urlshortener
    │   │   ├── UrlShortenerApplication.java
    │   │   ├── controller/UrlController.java       # REST endpoints
    │   │   ├── service/UrlService.java             # shorten + resolve + Redis cache logic
    │   │   ├── repository/UrlRepository.java      # Spring Data JPA
    │   │   ├── entity/UrlMapping.java              # table: url_mappings
    │   │   ├── dto/                                # request / response objects
    │   │   ├── exception/                          # custom exceptions + JSON error handler
    │   │   ├── config/AppProperties.java           # app.* settings
    │   │   └── util/Base62.java                    # short code generator
    │   └── resources/application.properties
    └── test/java/com/urlshortener
        ├── service/UrlServiceTest.java
        └── controller/UrlControllerTest.java
```

## Design Notes and Limitations

- **302 vs 301**: redirects use 302 (temporary) so browsers do not cache them forever and every visit can go through the service.
- **Random codes** are checked against the database before use. With 62^7 (about 3.5 trillion) possible codes, collisions are very rare.
- Submitting the same long URL twice creates two different short links.
- No authentication, custom aliases or link expiry yet.

## Possible Improvements

- Click counting / analytics per link
- Custom aliases and link expiry dates
- Rate limiting on the shorten endpoint

## License

MIT
