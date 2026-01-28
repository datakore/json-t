# Performance Benchmark figures

## Round 1 - without JVM args

> JMH version: 1.37<br />
> VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS<br />
> VM options: -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8<br />
> Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)<br />
> Warmup: 1 iterations, 10 s each<br />
> Measurement: 3 iterations, 30 s each<br />
> Timeout: 10 min per iteration<br />
> Threads: 1 thread, will synchronize iterations<br />
> Benchmark mode: Average time, time/op

### Benchmark Results 

| Benchmark                  | Record Count | Mode | Cnt | Score (ms/op)  | Error (ms/op) | Units |
|:---------------------------|:-------------| :--- |:----|:---------------|:--------------|:------|
| `benchmarkParseAndConvert` |   100,000    | avgt | 3   |   **9,254.63** | ±     679.14  | ms/op |
| `benchmarkParseAndConvert` |   500,000    | avgt | 3   |  **49,544.51** | ±  40,763.68  | ms/op |
| `benchmarkParseAndConvert` | 1,000,000    | avgt | 3   |  **99,761.47** | ±  18,354.46  | ms/op |
| `benchmarkParseAndConvert` | 2,000,000    | avgt | 3   | **249,852.32** | ± 217,321.07  | ms/op |
| `benchmarkStringify`       |   100,000    | avgt | 3   |   **2,006.63** | ±     427.77  | ms/op |
| `benchmarkStringify`       |   500,000    | avgt | 3   |  **10,000.91** | ±   1,747.14  | ms/op |
| `benchmarkStringify`       | 1,000,000    | avgt | 3   |  **25,224.34** | ± 137,220.82  | ms/op |
| `benchmarkStringify`       | 2,000,000    | avgt | 3   |  **70,576.00** | ±  85,604.88  | ms/op |

## File size comparison

| No of records | Json File Size                            | JsonT File Size                             | Schema Size | % Reduction<br/>JsonT/Json | % Schema space     | 
|:--------------|:------------------------------------------|:--------------------------------------------|:------------|:---------------------------|:-------------------|
| 1             | 3,650 [json](./marketplace_data-1.json)   | 2,288  [jsont](./marketplace_data-1.jsont)  | 1200        | 37.3%                      | 52.44%             |
| 10            | 42,528 [json](./marketplace_data-10.json) | 12,047 [jsont](./marketplace_data-10.jsont) | == Same ==  | 28.33%                     | 9.96%              |
| 100           | 391,296                                   | 98,141                                      | == Same ==  | 25.08%                     | 1.22%              |
| 1_000         | 4,096,064                                 | 1,024,568                                   | == Same ==  | 25.01%                     | 0.11% (negligible) |
| 10_000        | 40,839,489                                | 10,220,048                                  | == Same ==  | 25%                        | negligible         |
| 100_000       | 407,598,275                               | 101,976,245                                 | == same ==  | == same ==                 | negligible         |
| 200_000       | 815,465,540                               | 204,013,581                                 | == same ==  | == same ==                 | negligible         |
| 500_000       | 2,037,135,474                             | 509,657,941                                 | == same ==  | == same ==                 | negligible         |
| 1_000_000     | 4,072,728,905                             | 1,018,924,832                               | == same ==  | == same ==                 | negligible         |
