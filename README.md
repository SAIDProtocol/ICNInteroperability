# ICNInteroperability
## Before compile
### MobilityFirst (byte_array_guid branch) and ccnx 0.8.0
### In ccnx folder
```
mvn clean install -DskipTests=true -Dmaven.javadoc.skip=true
```
### In MF/mfclient/netapi/java 
```
mvn clean install
```

## Task list
- [x] Skeleton of adapters, demultiplexing items, canonical formats
- [x] Gateway with adapter interface
- [ ] IP Adapter
  - [x] Consumer side (left) static
  - [x] Provider side (right) static
  - [ ] Consumer side (left) dynamic
  - [ ] Provider side (right) dynamic
- [ ] IP Content provider
  - [ ] static (similar to file server)
  - [ ] dynamic (similar to ORS)
- [ ] NDN Adapter
  - [ ] Consumer side (left) static
  - [ ] Provider side (right) static
  - [ ] Consumer side (left) dynamic
  - [ ] Provider side (right) dynamic
- [ ] NDN Content provider
  - [x] static (use ccnr)
  - [ ] dynamic (similar to ORS)
- [ ] MF Adapter
  - [ ] Consumer side (left) static
  - [ ] Provider side (right) static
  - [ ] Consumer side (left) dynamic
  - [ ] Provider side (right) dynamic
- [ ] MF Content provider
  - [ ] static (similar to file server)
  - [ ] dynamic (similar to ORS)
- [ ] Optimization: chunked transmission  

