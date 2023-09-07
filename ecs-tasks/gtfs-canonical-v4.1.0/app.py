import sys, getopt
import sh

import argparse

def main(argv):

    argParser = argparse.ArgumentParser()
    argParser.add_argument("-i", "--input", help="Input file path")
    argParser.add_argument("-o", "--output", help="Outpu directory path")

    args = argParser.parse_args()
    inputfile = args.input
    outputdir = args.output
    print('Input file is ', inputfile)
    print('Output directory is ', outputdir)
    sh.java("-jar", "gtfs-validator-cli.jar", "-i", inputfile, "-o", outputdir)

if __name__ == "__main__":
    main(sys.argv[1:])
