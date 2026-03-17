# CampusFit Backend

Spring Boot backend for the CampusFit project.

## Stack
- JDK 21
- Spring Boot 3
- MySQL 8

## Run
1. Ensure local MySQL is available or update environment variables.
2. Start with `mvn spring-boot:run`
3. Default port: `8080`

## Environment variables
- `MYSQL_HOST`
- `MYSQL_PORT`
- `MYSQL_DATABASE`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`

## Current API skeleton
- `GET /api/health`
- `GET /api/posts/recommendations`
- `GET /api/posts/{id}`
- `GET /api/posts/mine`
- `POST /api/posts`
- `GET /api/profile/me`
- `GET /api/tags/options`
- `GET /api/admin/dashboard/summary`
