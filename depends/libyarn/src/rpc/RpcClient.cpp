/********************************************************************
 * Copyright (c) 2014, Pivotal Inc.
 * All rights reserved.
 *
 * Author: Zhanwei Wang
 ********************************************************************/
#include "Exception.h"
#include "ExceptionInternal.h"
#include "Logger.h"
#include "Memory.h"
#include "RpcClient.h"
#include "Thread.h"

#include <uuid/uuid.h>

namespace Yarn {
namespace Internal {

once_flag RpcClient::once;
shared_ptr<RpcClient> RpcClient::client;

void RpcClient::createSinglten() {
    client = shared_ptr < RpcClient > (new RpcClientImpl());
}

RpcClient & RpcClient::getClient() {
    call_once(once, &RpcClientImpl::createSinglten);
    assert(client);
    return *client;
}

RpcClientImpl::RpcClientImpl() :
    cleaning(false), running(true), count(0) {
    uuid_t id;
    uuid_generate(id);
    clientId.resize(sizeof(uuid_t));
    memcpy(&clientId[0], id, sizeof(uuid_t));
#ifdef MOCK
    stub = NULL;
#endif
}

RpcClientImpl::~RpcClientImpl() {
    running = false;
    cond.notify_all();

    if (cleaner.joinable()) {
        cleaner.join();
    }

    close();
}

void RpcClientImpl::clean() {
    assert(cleaning);

    try {
        while (running) {
            try {
                unique_lock<mutex> lock(mut);
                cond.wait_for(lock, seconds(1));

                if (!running || allChannels.empty()) {
                    break;
                }

                unordered_map<RpcChannelKey, shared_ptr<RpcChannel> >::iterator s, e;
                e = allChannels.end();

                for (s = allChannels.begin(); s != e;) {
                    if (s->second->checkIdle()) {
                        s->second.reset();
                        s = allChannels.erase(s);
                    } else {
                        ++s;
                    }
                }
            } catch (const YarnCanceled & e) {
                /*
                 * ignore cancel signal here.
                 */
            }
        }
    } catch (const Yarn::YarnException & e) {
        LOG(LOG_ERROR, "RpcClientImpl's idle cleaner exit: %s",
            GetExceptionDetail(e));
    } catch (const std::exception & e) {
        LOG(LOG_ERROR, "RpcClientImpl's idle cleaner exit: %s", e.what());
    }

    cleaning = false;
}

void RpcClientImpl::close() {
    lock_guard<mutex> lock(mut);
    running = false;
    unordered_map<RpcChannelKey, shared_ptr<RpcChannel> >::iterator s, e;
    e = allChannels.end();

    for (s = allChannels.begin(); s != e; ++s) {
        s->second->waitForExit();
    }

    allChannels.clear();
}

bool RpcClientImpl::isRunning() {
    return running;
}

RpcChannel & RpcClientImpl::getChannel(const RpcAuth & auth,
                                       const RpcProtocolInfo & protocol, const RpcServerInfo & server,
                                       const RpcConfig & conf) {
    shared_ptr<RpcChannel> rc;
    RpcChannelKey key(auth, protocol, server, conf);

    try {
        lock_guard<mutex> lock(mut);

        if (!running) {
            THROW(Yarn::YarnRpcException,
                  "Cannot Setup RPC channel to \"%s:%s\" since RpcClient is closing",
                  key.getServer().getHost().c_str(), key.getServer().getPort().c_str());
        }

        unordered_map<RpcChannelKey, shared_ptr<RpcChannel> >::iterator it;
        it = allChannels.find(key);

        if (it != allChannels.end()) {
            rc = it->second;
        } else {
            rc = createChannelInternal(key);
            allChannels[key] = rc;
        }

        rc->addRef();

        if (!cleaning) {
            cleaning = true;

            if (cleaner.joinable()) {
                cleaner.join();
            }

            CREATE_THREAD(cleaner, bind(&RpcClientImpl::clean, this));
        }
    } catch (const YarnRpcException & e) {
        throw;
    } catch (...) {
        NESTED_THROW(YarnRpcException,
                     "RpcClient failed to create a channel to \"%s:%s\"",
                     server.getHost().c_str(), server.getPort().c_str());
    }

    return *rc;
}

shared_ptr<RpcChannel> RpcClientImpl::createChannelInternal(
    const RpcChannelKey & key) {
    shared_ptr<RpcChannel> channel;
#ifdef MOCK

    if (stub) {
        channel = shared_ptr < RpcChannel > (stub->getChannel(key, *this));
    } else {
        channel = shared_ptr < RpcChannel > (new RpcChannelImpl(key, *this));
    }

#else
    channel = shared_ptr<RpcChannel>(new RpcChannelImpl(key, *this));
#endif
    return channel;
}

}
}
