#-------------------------------------------------------------------------
#
# Makefile--
#    Makefile for utils/hash
#
# IDENTIFICATION
#    $PostgreSQL: pgsql/src/backend/utils/hash/Makefile,v 1.13 2008/02/19 10:30:08 petere Exp $
#
#-------------------------------------------------------------------------

subdir = src/backend/utils/hash
top_builddir = ../../../..
include $(top_builddir)/src/Makefile.global

#override CPPFLAGS :=-msse4.2 $(CPPFLAGS)

OBJS = dynahash.o hashfn.o pg_crc.o crc32c.o

include $(top_srcdir)/src/backend/common.mk
