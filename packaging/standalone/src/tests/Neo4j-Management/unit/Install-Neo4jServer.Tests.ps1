$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$sut = (Split-Path -Leaf $MyInvocation.MyCommand.Path).Replace(".Tests.", ".")
$common = Join-Path (Split-Path -Parent $here) 'Common.ps1'
. $common

Import-Module "$src\Neo4j-Management.psm1"

InModuleScope Neo4j-Management {
  Describe "Install-Neo4jServer" {

    # Setup mocking environment
    #  Mock Java environment
    $javaHome = global:New-MockJavaHome
    Mock Get-Neo4jEnv { $javaHome } -ParameterFilter { $Name -eq 'JAVA_HOME' } 
    Mock Test-Path { $false } -ParameterFilter {
      $Path -like 'Registry::*\JavaSoft\Java Runtime Environment'
    }
    Mock Get-ItemProperty { $null } -ParameterFilter {
      $Path -like 'Registry::*\JavaSoft\Java Runtime Environment*'
    }
    # TODO Refactor service name into mock neo4j
    $serviceName = 'neo4j'
    Mock Get-Neo4jWindowsServiceName { return $serviceName }
    Mock Start-Process { throw "Should not call Start-Process mock" }

    Context "Invalid or missing specified neo4j installation" {
      $serverObject = global:New-InvalidNeo4jInstall
 
      It "return throw if invalid or missing neo4j directory" {
        { Install-Neo4jServer -Neo4jServer $serverObject  -ErrorAction Stop }  | Should Throw      
      }
    }

    Context "Invalid or missing servicename in specified neo4j installation" {
      Mock Get-Neo4jWindowsServiceName { throw "Could not determine Service Name" }
      $serverObject = global:New-InvalidNeo4jInstall
 
      It "return throw if invalid or missing service name" {
        { Install-Neo4jServer -Neo4jServer $serverObject  -ErrorAction Stop }  | Should Throw      
      }
    }

    Context "Windows service already exists" {
      Mock Get-Service -Verifiable { return 'Service Exists' }
      Mock New-Service { throw "Should not call New-Service"}

      $serverObject = global:New-InvalidNeo4jInstall
      
      $result = Install-Neo4jServer -Neo4jServer $serverObject
      It "returns 0 for service that already exists" {
        $result | Should Be 0
      }
      
      It "calls verified mocks" {
        Assert-VerifiableMocks
      }
    }

    Context "Installs windows service with failure" {
      Mock Start-Process -Verifiable { throw "Error installing" }

      $serverObject = global:New-MockNeo4jInstall

      It "throws when error during installation" {
        { Install-Neo4jServer -Neo4jServer $serverObject } | Should Throw
      }
      
      It "calls verified mocks" {
        Assert-VerifiableMocks
      }
    }

    Context "Installs windows service with success" {
      Mock Start-Process -Verifiable { @{ 'ExitCode' = 0} }

      $serverObject = global:New-MockNeo4jInstall 

      $result = Install-Neo4jServer -Neo4jServer $serverObject

      It "returns 0 when succesfully installed" {
        $result | Should Be 0
      }
      
      It "calls verified mocks" {
        Assert-VerifiableMocks
      }
    }

  }
}
