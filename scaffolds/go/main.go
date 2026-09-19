package main

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
)

var config = struct {
	Port       string
	FleetURL   string
	PartnerURL string
	BusURL     string
}{
	Port:       envOr("PORT", "3000"),
	FleetURL:   envOr("FLEET_URL", "http://localhost:4001"),
	PartnerURL: envOr("PARTNER_URL", "http://localhost:4002"),
	BusURL:     envOr("BUS_URL", "http://localhost:4003"),
}

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func main() {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
	})

	log.Printf("listening on :%s", config.Port)
	log.Fatal(http.ListenAndServe(":"+config.Port, mux))
}
