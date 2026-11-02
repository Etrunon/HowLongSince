FROM golang:1.23-alpine AS build
WORKDIR /src
COPY go.mod *.go index.html ./
RUN go test ./... && CGO_ENABLED=0 go build -ldflags="-s -w" -o /hls .

FROM scratch
COPY --from=build /hls /hls
USER 1000:1000
ENTRYPOINT ["/hls"]
