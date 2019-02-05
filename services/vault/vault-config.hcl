storage "file" {
  path = "/opt/cavirin/vaultdir/data"
}
listener "tcp" {
  address = "0.0.0.0:8200"
  tls_disable = 0
  tls_cert_file = "/opt/cavirin/vaultdir/certs/server.crt"
  tls_key_file = "/opt/cavirin/vaultdir/certs/server.key"
  tls_require_and_verify_client_cert = "false"
}
disable_mlock=true
